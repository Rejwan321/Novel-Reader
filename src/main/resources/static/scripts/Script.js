//Search bar
$(document).ready(function(){

    $(".fa-search").click(function(){
        $(".search-bar, .input").toggleClass("active");
        $(".input[type='text']").focus();
    });

});

//show and hide login
const showPopupBtn = document.querySelector(".log");

showPopupBtn.addEventListener("click",() =>{
    document.body.classList.toggle("show-popup");
});

document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
        showPopupBtn.click();
    }
});

//sliding login funtion
const signUp = document.getElementById('signup');
const login = document.getElementById('login');
const container = document.getElementById('container');

signUp.addEventListener('click', () => {
    container.classList.add('right-panel-active');
})
login.addEventListener('click', () => {
    container.classList.remove('right-panel-active');
});